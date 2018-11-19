from Common import *


def do_reinforcement_learning(q_table, state, action, next_state, reward):
    q_predict = q_table.loc[state, action]
    if next_state != TOTAL_STATES - 1:
        q_target = reward + GAMMA * q_table.iloc[next_state, :].max()  # 实际的(状态-行为)值 (回合没结束)
    else:
        q_target = reward  # 实际的(状态-行为)值 (回合结束)
    q_table.loc[state, action] += ALPHA * (q_target - q_predict)  # q_table 更新